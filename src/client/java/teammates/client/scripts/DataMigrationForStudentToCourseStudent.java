package teammates.client.scripts;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import javax.jdo.Query;

import teammates.client.remoteapi.RemoteApiClient;
import teammates.common.datatransfer.StudentAttributes;
import teammates.common.datatransfer.StudentWithOldRegistrationKeyAttributes;
import teammates.common.exception.InvalidParametersException;
import teammates.common.util.Assumption;
import teammates.storage.api.StudentsDb;
import teammates.storage.datastore.Datastore;
import teammates.storage.entity.Student;

public class DataMigrationForStudentToCourseStudent extends RemoteApiClient {
    
    private static final boolean isPreview = true;
    
    // When using ScriptTarget.BY_TIME, numDays can be changed to target
    // questions created in the past number of days
    private static final int numDays = 100;
    
    // When using ScriptTarget.BY_COURSE, specify the course to target with courseId
    private static final String courseId = "";
    
    /**
     * BY_TIME: migration will affect students created in the past {@code numDays} days
     * BY_COURSE: migration will affects students in the specified {@code courseId}
     * ALL: all students will be migrated
     */
    private enum ScriptTarget {
        BY_TIME, BY_COURSE, ALL;
    }
    
    ScriptTarget target = ScriptTarget.BY_TIME;
    
    private StudentsDb studentsDb = new StudentsDb();
    
    public static void main(String[] args) throws IOException {
        new DataMigrationForStudentToCourseStudent().doOperationRemotely();
    }

    @Override
    protected void doOperation() {
        Datastore.initialize();

        List<StudentAttributes> students = getOldStudentsForMigration(target);
        
        System.out.println("Creating a CourseStudent copy of students ...");
        
        try {
            for (StudentAttributes student : students) {
                StudentWithOldRegistrationKeyAttributes studentToSave =
                        studentsDb.getStudentForCopyingToCourseStudent(student.course, student.email);
                
                if (isPreview) {
                    System.out.println("Preview: will copy " + studentToSave.getIdentificationString());
                    continue;
                }
                
                // This replaces any copy of CourseStudent if it already exist
                studentsDb.createEntityWithoutExistenceCheck(studentToSave);
                System.out.println("Created CourseStudent for " + studentToSave.getIdentificationString());
                
            }
        } catch (InvalidParametersException e) {
            e.printStackTrace();
        }
    }
    
    private List<StudentAttributes> getOldStudentsForMigration(ScriptTarget target) {
        List<StudentAttributes> students;
        if (target == ScriptTarget.BY_TIME) {
            Calendar startCal = Calendar.getInstance();
            startCal.add(Calendar.DAY_OF_YEAR, -1 * numDays);
            
            students = getOldStudentsSince(startCal.getTime());
            
        } else if (target == ScriptTarget.BY_COURSE) {
            students = getOldStudentsForCourse(courseId);
            
        } else if (target == ScriptTarget.ALL) {
            students = getAllOldStudents();
            
        } else {
            students = null;
            Assumption.fail("no target selected");
        }
        return students;
    }

    private List<StudentAttributes> getOldStudentsSince(Date date) {
        String query = "SELECT FROM " + Student.class.getName()
                + " WHERE createdAt >= startDate"
                + " PARAMETERS java.util.Date startDate";
        @SuppressWarnings("unchecked")
        List<Student> oldStudents =
                (List<Student>) Datastore.getPersistenceManager().newQuery(query).execute(date);
        return getListOfStudentAttributes(oldStudents);
    }

    private List<StudentAttributes> getOldStudentsForCourse(String courseId) {
        Query q = Datastore.getPersistenceManager().newQuery(Student.class);
        q.declareParameters("String courseIdParam");
        q.setFilter("courseID == courseIdParam");
        
        @SuppressWarnings("unchecked")
        List<Student> oldStudents = (List<Student>) q.execute(courseId);
        
        return getListOfStudentAttributes(oldStudents);
    }
    
    private List<StudentAttributes> getListOfStudentAttributes(List<Student> oldStudents) {
        List<StudentAttributes> students = new ArrayList<>();
        for (Student oldStudent : oldStudents) {
            students.add(new StudentAttributes(oldStudent));
        }
        return students;
    }

    @SuppressWarnings("deprecation")
    private List<StudentAttributes> getAllOldStudents() {
        return studentsDb.getAllOldStudents();
    }

}
